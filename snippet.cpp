/*TODO
*
** 
	Dato un file, diviso in:
	- 10 righe di intestazione
	- n righe di dati
	- 1 riga di EOF
	Modificare i dati presenti dopo il secondo spazio delle righe di dati con dei valori contenuti in un vettore.

	Quindi:
	1) Leggere il file partendo dalla 11 riga, o dalla prima ma leggendone 10 prima di iniziare a sovrascrivere i valori.
	2) Contare gli spazi presenti nella riga. Una volta arrivati a 2 spazi (o 1 se si inizia a contare da 0) inizio a riscrivere i valori.
	3) Passare alla riga successiva con il contatore degli spazi ri-inizializzato. 

	Metodo alternativo: al posto di inizializzare ogni volta il contatore, posso farlo partire da 1 e quando contatore%2==0 sovrascrivere.

	Punti critici:
	- Quando incontro un /n o /r (o qualsiasi cosa che vada a capo) devo fare in modo che mi lasci finire di scrivere se non ho ancora finito.
	- Se finisco di scrivere prima di aver cancellato tutto ciò che c'era scritto in precedenza, devo cancellare e spostare il segnale di fine riga.

	OSSERVAZIONI 
	- il file su cui fafre le modifiche qui si chiama "input.txt"
**
*
*/

#include <iostream>
#include <math.h>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>
#include <array>

using namespace std;

std::array<float, 4> testArray{{2.03873,-3.973289,1,10}};

std::stringstream stringStream;

void sovrascrivi(std::string s, int num_riga, int starting_pos);

int main()
{
	//std::fstream file;

	std::cout << "Creating file" << std::endl;

	//FILE* file;
	std::fstream file("input.txt");

	std::cout << "File created" << std::endl;

	//file = fopen("input.txt", "w");

	if (!file)
	{
		std::cout << "An error occured opening the file." << std::endl;
		return -1;
	}

	else
	{
		std::string stringa;
		int row_count = 0;
		int space_count_global = 0;
		int space_count_string = 0;
		int mod; 

		std::cout << "File opened" << std::endl;

		while(std::getline(file,stringa))
		{
			//std::cout << "sono nel while!" << std::endl << std::endl;
			if(row_count<10)
			{
				std::cout << "sto leggendo la righa: " << row_count << " = " << stringa << std::endl;
				stringStream << stringa << std::endl;
			}
			else
			{
				int i=0;
				while(i < stringa.length() )
				{
					if(isspace(stringa[i]))
					{	
						//std::cout << "Ho trovato uno spazio nella riga: " << row_count << " = " << stringa << std::endl;
						//std::cout << " Lo spazio è in pos: " << i << std::endl;
						space_count_string++;
						if(space_count_string < 3 )
						{
							//std::cout << "Incremento il contatore di spazi globale della stringa numero: " << row_count << std::endl;
							space_count_global++;
							//std::cout << "contatore spazi globale = " << space_count_global << std::endl;
							if(space_count_global > 1)
							{
								std::cout << "Ho trovato entrambi gli spazi, sovrascrivo la riga numero: " << row_count << " = " << stringa << std::endl;
								//std::cout << " Partendo dalla posizione " << i << std::endl;
								// Devo sovrascrivere la stringa s partendo dalla posizione row_count, inserendo il valore carattere per carattere
								sovrascrivi(stringa, row_count, i+1);
								space_count_global = 0;
							}
						}	
					}
					i++;
				}
				//std::cout << "sono nell'else e riga: " << stringa << '\n';				
				space_count_string = 0;	
			}
			row_count++;
		}
	}		
	/*
				if (space_count_global > 1)
					{
						std::cout << "Ho trovato entrambi gli spazi, sovrascrivo la riga numero: " << row_count << " = " << stringa << std::endl;
						std::cout << " Partendo dalla posizione " << i << std::endl;
						mod = (row_count - 10)%4;
						std::string elem = to_string(testArray[mod]);
						for (int j=0; j<=elem.length(); j++)
						{
							std::cout << "stringa [" << i << "] : " << stringa[i] << " elem [" << j <<"] : " << elem[j] << std::endl;
							stringa[i] = elem[j];
							i++; 	
						}
						std::cout << "Dovrei aver sovrascritto la stringa: " << stringa << std::endl << std::endl;
						space_count_global = 0;
					}
	*/

	file.close();
	//fclose(file);
	std::cout << "File closed" << std::endl;

	std::ofstream f;
	f.open("input.txt", std::ofstream::out);

	if( !(f.is_open()))
	{
		std::cout << "Error opening the file for the 2nd time" << std::endl;
		return -1;
	}
	else
	{
		std::cout << "Ho aperto il file per scrivere" << std::endl;
		f << stringStream.str();
		f.close();
	}

	return 0;
}

void sovrascrivi(std::string s, int num_riga, int starting_pos)
{
	int mod = (num_riga-10)%4;
	
	std::cout << "In sovrascrivi mod = " << mod << std::endl;
	std::cout << "sovrascrivo la stringa " << s << " riga " << num_riga << " partendo da " << starting_pos << std::endl;

	std::string d = to_string(testArray[mod]);

	std::cout << "to string di test array in posizione: " << d << std::endl;
	
	//Imposto i flag per vedere in che caso sono 
	if ((s.length() - starting_pos) < d.length())
	{
		int shift = d.length() - (s.length() - starting_pos);
		int size = s.length();
		s.resize(size+shift);
	}
	else
	{
		int shift = (s.length() - starting_pos) - d.length();
		int size = s.length();
		s.resize(size+shift);	
	}

	int j=0;
	while(starting_pos < s.length() || j<d.length())
	{
		std::cout << "stringa [" << starting_pos << "] : " << s[starting_pos] << " elem [" << j <<"] : " << d[j] << std::endl;
		s[starting_pos] = d[j];
		starting_pos++;
		j++;
	}

	std::cout << "stringa modificata nella funzione : " << s << std::endl;
	stringStream << s << std::endl;
}